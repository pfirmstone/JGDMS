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
 * This class is used to create a singly-linked list of written nodes
 * for notification and searches.  When an entry is written, it is
 * added to this list.  As each thread interested in recently-written
 * entries works, it has a reference to the <code>WrittenEntry</code>
 * object that mark the last entry the thread examined.  When it is
 * done with that entry, it proceeds to the next.  As each of these
 * references progresses, the <code>WrittenEntry</code> nodes in the
 * list get left behind to be collected as garbage.
 * <p>
 * The list initially starts with the return value of <code>startNode</code>,
 * and nodes are added using <code>add</code>.  The constructor is private to
 * enforce this usage.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see OutriggerServerImpl#write
 * @see Notifier
 * 
 */
// @see OutriggerServerImpl#getMatch(EntryRep,Transaction,long,boolean,boolean,RemoteEventListener,long)
class WrittenEntry {
    /** The time at which this entry was written. */
    private long		timestamp;

    /** The next node in the list. */
    private WrittenEntry	next;

    /** The EntryRep this node refers to. */
    private EntryRep		rep;

    /**
     * Create a new time-stamped entry for the given EntryRep.  The comment
     * for the class explains why this is private.
     */
    private WrittenEntry(EntryRep rep) {
	this.rep = rep;
	timestamp = System.currentTimeMillis();
    }

    /**
     * Return the node which is the head of the list.  It is time-stamped,
     * and has an <code>EntryRep</code> of <code>null</code>.
     */
    static WrittenEntry startNode() {
	return new WrittenEntry(null);
    }

    /**
     * Add a new entry after this one.  If this entry has already had a node
     * added (possibly by another thread), <code>add</code> will skip ahead to
     * the end of the list.
     */
    synchronized WrittenEntry add(EntryRep rep) {
	WrittenEntry entry = this;
	for (entry = this; entry.next != null; entry = entry.next)
	    continue;
	return (entry.next = new WrittenEntry(rep));
    }

    /**
     * Return the time stamp when this entry was written.
     */
    long timestamp() {
	return timestamp;
    }

    /**
     * Return the EntryRep of this entry.  If this is the first node (the one
     * returned by <code>startNode</code>), the EntryRep is <code>null</code>.
     *
     * @see #startNode
     */
    EntryRep rep() {
	return rep;
    }

    /**
     * Return the next object in the list.
     */
    WrittenEntry next() {
	return next;
    }

    public String toString() {
	return
	    (rep + " (" + timestamp + ") ->" + (next == null ? " null" : ""));
    }
}
