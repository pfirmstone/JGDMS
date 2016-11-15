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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

/**
 * <code>TemplateHandle</code> associates one or more
 * <code>TransitionWatcher</code>s with a template.
 * Unless otherwise noted all methods are thread safe.
 */
class TemplateHandle extends BaseHandle {
    
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
    final private Set<TransitionWatcher> watchers 
            = Collections.newSetFromMap(
                        new ConcurrentHashMap<TransitionWatcher,Boolean>());
    /**
     * WriteLock guarantees that no updates can be performed during a 
     * removal operation.
     */
    final private WriteLock wl;
    final private ReadLock rl;
    private boolean removed; // mutate with wl, read with rl

    /**
     * The <code>WatchersForTemplateClass</code> this object
     * belongs to.
     */
    final private OutriggerServerImpl owner;

    /**
     * Create a handle for the template <code>tmpl</code>.
     */
    TemplateHandle(EntryRep tmpl, OutriggerServerImpl owner, Queue<TemplateHandle> content) {
	super(tmpl, content);
	this.owner = owner;
        ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
        wl = rwl.writeLock();
        rl = rwl.readLock();
    }

    /**
     * Return the description for the given field count.
     */
    EntryHandleTmplDesc descFor(int index) {
        return EntryHandle.descFor(rep(), index);
    }

    /**
     * Return <code>true</code> if this template matches the given entry.
     */
    boolean matches(EntryRep entry) {
	return rep().matches(entry);
    }

    /** 
     * Add a watcher to this handle. Assumes that the handle has not
     * been removed.
     * @param watcher the watcher to be added.
     * @return true if watcher is added, false otherwise.
     * @throws NullPointerException if watcher is <code>null</code>.
     */
    boolean addTransitionWatcher(TransitionWatcher watcher) {
        if (watcher == null)
	    throw new NullPointerException("Watcher can not be null");
        rl.lock();
        try {
            if (removed) return false;
            if (watcher.addTemplateHandle(this)) {
                return watchers.add(watcher);
            }
            return false;
        } finally {
            rl.unlock();
        }
    }

    /**
     * Remote a watcher from this handle. Does nothing 
     * if the specified watcher is not associated with
     * this <code>TemplateHandle</code>.
     * @param watcher the watcher to be removed.
     * @throws NullPointerException if watcher is <code>null</code>.
     */
    void removeTransitionWatcher(TransitionWatcher watcher) {
	if (watcher == null)
	    throw new NullPointerException("Watcher can not be null");
        rl.lock();
        try {
            watchers.remove(watcher);
        } finally {
            rl.unlock();
        }
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
    void collectInterested(Set<TransitionWatcher> set, EntryTransition transition,
					long ordinal) 
    {
        rl.lock();
        try {
            if (removed) return;
            final Iterator i = watchers.iterator();
            while (i.hasNext()) {
                final TransitionWatcher w = (TransitionWatcher)i.next();
                if (w.isInterested(transition, ordinal)) {
                    set.add(w);
                }
            }
        } finally {
            rl.unlock();
        }
    }

    /**
     * Return the <code>OutriggerServerImpl</code> this 
     * handle is part of.
     * @return The <code>OutriggerServerImpl</code> this 
     * handle is part of.
     */
    OutriggerServerImpl getServer() {
	return owner;
    }

    /**
     * Visit each <code>TransitionWatcher</code> and check to see if
     * it has expired, removing it if it has.
     * @param now an estimate of the current time expressed as
     *            milliseconds since the beginning of the epoch.
     */
    void reap(long now) {
        rl.lock();
        try{
            Iterator<TransitionWatcher> it = watchers.iterator();
            while (it.hasNext()){
                it.next().removeIfExpired(now);
            }
        } finally {
            rl.unlock();
        }
    }

    
    /**
     * Need to lock on the wl so no one will
     * add a watcher between the check for empty and
     * when it gets removed.
     */
    boolean removeIfEmpty(){
        wl.lock();
        try {
            if (watchers.isEmpty()) {
                return remove();
            }
            return false;
        } finally {
            wl.unlock();
        }
    }

    @Override
    public boolean removed() {
        rl.lock();
        try {
            return removed;
        } finally {
            rl.unlock();
        }
    }

    @Override
    public boolean remove() {
        wl.lock();
        try {
            if (removed){
                return false; // already removed.
            } else {
                removed = super.remove();
                return removed;
            }
        } finally {
            wl.unlock();
        }
    }
}
