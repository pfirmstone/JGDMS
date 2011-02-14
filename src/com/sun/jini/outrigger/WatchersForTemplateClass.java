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
 * Holds a collection of <code>TemplateHandle</code>s who's templates
 * are all of exactly the same class. Unless otherwise noted all
 * methods are thread safe. This method provides the linkage between
 * <code>TemplateHandle</code>s and <code>TransitionWatchers</code>
 * and for the most part is not visible to the clients of either.
 */
class WatchersForTemplateClass {
    /** All the templates we know about */
    private final FastList<TemplateHandle> contents = new FastList<TemplateHandle>();	

    /** The object we are inside of */
    private final TransitionWatchers owner;

    /**
     * Create a new <code>WatchersForTemplateClass</code> object
     * associated with the specified <code>TransitionWatchers</code> object.
     * @param owner The <code>TransitionWatchers</code> that
     *              this object will be a part of.
     * @throws NullPointerException if <code>owner</code> is
     *         <code>null</code>.
     */
    WatchersForTemplateClass(TransitionWatchers owner) {
	if (owner == null)
	    throw new NullPointerException("owner must be non-null");
	this.owner = owner;
    }

    /**
     * Add a <code>TransitionWatcher</code> to the list
     * of watchers looking for visibility transitions in
     * entries that match the specified template. Associates
     * a <code>TemplateHandle</code> using 
     * <code>TransitionWatcher.setTemplateHandle</code> method.
     *
     * @param watcher The <code>TransitionWatcher</code> being added.
     * @param template The <code>EntryRep</code> that represents
     *                 the template of interest.
     * @throws NullPointerException if either argument is
     *         <code>null</code>.
     */
    void add(TransitionWatcher watcher, EntryRep template) {
	/* We try to find an existing handle, but it is ok
	 * if we have more than one with the same template. It
	 * is bad if we add the watcher to a removed handle.
	 */	
        for(TemplateHandle handle : contents) {
	    if (template.equals(handle.rep())) {
		synchronized (handle) {
		    if (!handle.removed()) {
			/* Found one, add and break. Call
			 * addTemplateHandle() before adding to handle
			 * so if the handle calls the watcher it will
			 * be in a complete state. Add inside the
			 * lock so handle can't be removed.
			 */
			if (watcher.addTemplateHandle(handle)) {
			    handle.addTransitionWatcher(watcher);
			} // else the watcher was removed, don't add to handle

			return; // found a handle and added the watcher
		    }
		}
	    }
	}

	/* If we are here we could not find a handle with the right
	 * template, create one, add the watcher to it, and it
	 * to contents.
	 */
	TemplateHandle handle = new TemplateHandle(template, this);

	/* We need the sync both to prevent concurrent modification
	 * of handle (since we add it to contents first), and to
	 * make sure other threads see the changes we are about to make.
	 */
	synchronized (handle) {
	    /* First add handle to contents so handle is fully initialized 
	     * before we start to pass it around use
	     */
	    contents.add(handle);
	    if (watcher.addTemplateHandle(handle)) {
		handle.addTransitionWatcher(watcher);
	    } else {
		// watcher is already dead, undo adding handle
		contents.remove(handle);				    
	    }
	}
    }
    
    /**
     * Iterate over the watchers associated with 
     * this object calling <code>isInterested</code> on each
     * and if it returns <code>true</code> adding the watcher to the
     * passed set.
     *
     * @param set The set to accumulate interested watchers
     *            into.
     * @param transition The transition being processed.
     * @param ordinal The ordinal associated with <code>transition</code>.
     * @throws NullPointerException if either argument is <code>null</code>.
     */
    void collectInterested(Set set, EntryTransition transition, 
			   long ordinal) 
    {
	final EntryHandle entryHandle = transition.getHandle();
	final EntryRep rep = entryHandle.rep();
	final long entryHash = entryHandle.hash();
	final int repNumFields = rep.numFields();

	/* Look at each of handles, check to see if they match
	 * the changed entry and if they do ask them to 
	 * put the appropriate watchers in the set.
	 */
	for (TemplateHandle handle : contents)
	{
	    // See the if handle mask is incompatible
	    EntryHandleTmplDesc desc = handle.descFor(repNumFields);

	    if ((entryHash & desc.mask) != desc.hash)
		continue;

	    if (handle.matches(rep)) {
		if (handle.removed()) //no sync, ok if we check a removed one
		    continue;
		handle.collectInterested(set, transition, ordinal);
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
     * it has expired, removing it if it has. Also reaps the
     * <code>FastList</code> associated with this object.
     * @param now an estimate of the current time expressed as
     *            milliseconds since the beginning of the epoch.
     */
    void reap(long now) {
	// First remove empty handles
	for (TemplateHandle handle : contents)
	{
	    // Dump any expired watchers.
	    handle.reap(now);

	    /* Need to lock on the handle so no one will
	     * add a watcher between the check for empty and
	     * when it gets marked removed.
	     */
	    synchronized (handle) {
		if (handle.isEmpty()) {
		    contents.remove(handle);
		    continue;
		}
	    }
	}

	/* This provides the FastList with an opportunity to actually
	 * excise the items identified as "removed" from the list.
	 */
	contents.reap();
    }
}

