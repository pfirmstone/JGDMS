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
package org.apache.river.outrigger;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Holds a collection of <code>TemplateHandle</code>s who's templates
 * are all of exactly the same class. Unless otherwise noted all
 * methods are thread safe. This method provides the linkage between
 * <code>TemplateHandle</code>s and <code>TransitionWatchers</code>
 * and for the most part is not visible to the clients of either.
 */
class WatchersForTemplateClass {
    /** All the templates we know about */
    private final Queue<TemplateHandle> content = new ConcurrentLinkedQueue<TemplateHandle>();

    /** The OutriggerServerImpl we belong to */
    private final OutriggerServerImpl owner;

    /**
     * Create a new <code>WatchersForTemplateClass</code> object
     * associated with the specified <code>TransitionWatchers</code> object.
     * @param owner The <code>OutriggerServerImpl</code> that
     *              this object will be a part of.
     * @throws NullPointerException if <code>owner</code> is
     *         <code>null</code>.
     */
    WatchersForTemplateClass(OutriggerServerImpl owner) {
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
	 * if we have more than one with the same template.
         * It isn't possible to add a watcher to a removed
         * handle even if present during iteration.
	 */	
        for(TemplateHandle handle : content) {
	    if (template.equals(handle.rep()) &&
                handle.addTransitionWatcher(watcher)) return;
	}
        
	TemplateHandle handle = new TemplateHandle(template, owner, content);
        if (handle.addTransitionWatcher(watcher)) content.add(handle);
        // else the new handle is discarded.
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
    void collectInterested(Set<TransitionWatcher> set, EntryTransition transition, 
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
	for (TemplateHandle handle : content)
	{
	    // See the if handle mask is incompatible
	    EntryHandleTmplDesc desc = handle.descFor(repNumFields); // final

	    if ((entryHash & desc.mask) != desc.hash) continue;

	    if (handle.matches(rep)) handle.collectInterested(set, transition, ordinal);
	}
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
	for (TemplateHandle handle : content)
	{
	    // Dump any expired watchers.
	    handle.reap(now);
            handle.removeIfEmpty();
	}
    }
}

