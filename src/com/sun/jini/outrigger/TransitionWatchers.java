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

import java.util.Map;
import java.util.Collection;
import java.util.SortedSet;

/**
 * Given an <code>EntryHandle</code> who's entry is making a
 * visibility transition this class will find all the 
 * <code>TransitionWatcher</code>s who are interested in that
 * transition. The <code>TransitionWatcher</code>s are organized
 * into groups using <code>TemplateHandle</code>. Each
 * <code>TemplateHandle</code> aggregates a number of watchers
 * all interested in the same template.
 * 
 * @see TransitionWatcher
 * @author Sun Microsystems, Inc.
 */
class TransitionWatchers {
    /** 
     * A map from class names to <code>WatchersForTemplateClass</code>
     * objects 
     */
    final private Map holders = new java.util.HashMap();

    /** The server we are working for */
    final private OutriggerServerImpl server;

    /**
     * Create a new <code>TransitionWatchers</code> object
     * for the specified server.
     * @param server The server the new <code>TransitionWatchers</code> 
     *               object is working for.
     * @throws NullPointerException if <code>server</code> is 
     *        <code>null</code>
     */
    TransitionWatchers(OutriggerServerImpl server) {
	if (server == null)
	    throw new NullPointerException("server must be non-null");
	this.server = server;
    }

    /**
     * Add a <code>TransitionWatcher</code> to the list
     * of watchers looking for visibility transitions in
     * entries that match the specified template. Associates
     * a <code>TemplateHandle</code> using 
     * <code>TransitionWatcher.setTemplateHandle</code> method.
     * <p>
     * This method is thread safe. The watcher added in this call is
     * guaranteed to be consulted by the next call to
     * <code>allMatches</code> that starts after this call completes even
     * if that call is made from another thread. Also, all of
     * of the assigned values in the calling thread's working
     * memory will be copied out to main memory as part of the 
     * process of making the passed watcher visible to future
     * <code>allMatches</code> and <code>findTransitionWatcher</code> calls.
     *
     * @param watcher The <code>TransitionWatcher</code> being added.
     * @param template The <code>EntryRep</code> that represents
     *                 the template of interest.
     * @throws NullPointerException if either argument is
     *         <code>null</code>.  
     */
    void add(TransitionWatcher watcher, EntryRep template) {
	// Get/create the appropriate WatchersForTemplateClass
	final String className = template.classFor();
	WatchersForTemplateClass holder;
	synchronized (holders) {
	    holder = (WatchersForTemplateClass) holders.get(className);	    
	    if (holder == null) {
		holder = new WatchersForTemplateClass(this);
		holders.put(className, holder);
	    }
	}

	// Add the watcher to the WatchersForTemplateClass
	holder.add(watcher, template);
    }

    /**
     * Return a <code>SortedSet</code> of all the
     * <code>TransitionWatcher</code> who's <code>isInterested</code>
     * methods return <code>true</code> when asked about the specified
     * visibility transition.
     * <p>
     * This method is thread safe. This call is guaranteed to check unremoved
     * watchers that were added by <code>add</code> calls that completed
     * before this call started, even if the calls were made from
     * different threads. Before the <code>isInterested</code> method
     * of the first watcher is called the working memory of this thread
     * will be flushed so any changes made to main memory before
     * this call started will be visible.
     * 
     * @param transition A <code>EntryTransition</code> that
     *              describes the transition and what
     *              entry is transitioning. This method
     *              will assume that <code>transition.getHandle</code>
     *              returns a non-null value.
     * @param ordinal The ordinal associated with <code>transition</code>.
     * @return A new <code>SortedSet</code> of all the 
     *         <code>TransitionWatcher</code>s interested in the specified
     *         visibility transition. If none are interested an empty
     *         map will be returned.
     * @throws NullPointerException if <code>transition</code> is 
     *         <code>null</code>.
     */
    SortedSet allMatches(EntryTransition transition, long ordinal) {
	final EntryRep rep = transition.getHandle().rep();
	final SortedSet rslt = new java.util.TreeSet();
	final String className = rep.classFor();
	WatchersForTemplateClass holder;
	
	/* Collect all the watchers looking for the exact class of the
	 * transitioned entry. Sync both to protect holder, and
	 * to make sure we do at least one sync so we see any
	 * watchers added before this call.
	 */
	synchronized (holders) {
	    holder = (WatchersForTemplateClass)holders.get(className);	    
	}

	if (holder != null)
	    holder.collectInterested(rslt, transition, ordinal);

	// Get all the templates that are super classes of className
	final String[] superclasses = rep.superclasses();
	for (int i=0; i<superclasses.length; i++) {	    
	    synchronized (holders) {
		holder = 
		    (WatchersForTemplateClass)holders.get(superclasses[i]);
	    }
	    if (holder != null)
		holder.collectInterested(rslt, transition, ordinal);
	}

	// Including those registered for the null template
	final String nullClass = EntryRep.matchAnyEntryRep().classFor();
	synchronized(holders) {
	    holder =
		(WatchersForTemplateClass)holders.get(nullClass);
	}
        
	if (holder!=null){
	    holder.collectInterested(rslt, transition, ordinal);
	}
	
	return rslt;
    }

    /**
     * Visit each <code>TransitionWatcher</code> and check to see if
     * it has expired, removing it if it has.  Also reap the
     * <code>FastList</code>s.
     */
    void reap() {
 	/* This could take a while, instead of blocking all other
	 * access to handles, clone the contents of handles and
	 * iterate down the clone (we don't do this too often and
	 * handles should never be that big so a shallow copy should
	 * not be that bad, if it did get to be bad caching the
	 * clone would probably work well since we don't add (and
	 * never remove) elements that often.)
	 */
	final WatchersForTemplateClass content[];
	synchronized (holders) {
	    final Collection values = holders.values();
	    content = new WatchersForTemplateClass[values.size()];
	    values.toArray(content);
	}

	final long now = System.currentTimeMillis();
	for (int i=0; i<content.length; i++) {
	    content[i].reap(now);
	}	    
    }

    /**
     * Return the <code>OutriggerServerImpl</code> this 
     * <code>TransitionWatchers</code> object is part of.
     * @return The <code>OutriggerServerImpl</code> this 
     * <code>TransitionWatchers</code> is part of.
     */
    OutriggerServerImpl getServer() {
	return server;
    }

}
