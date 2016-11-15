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

import java.util.Hashtable;
import java.util.Map;
import java.util.Collection;

import net.jini.core.lease.Lease;
import net.jini.id.Uuid;
import org.apache.river.landlord.LeasedResource;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A set of <code>EntryHolder</code> objects for a given space.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see EntryHolder
 * @see OutriggerServerImpl
 */
final class EntryHolderSet {
    private final ConcurrentMap<String,EntryHolder> holders = new ConcurrentHashMap<String,EntryHolder>();
    // Map of LeaseDescs indexed by the cookie of the underlying
    // LeasedResource.
    private final ConcurrentMap<Uuid, EntryHandle> idMap = new ConcurrentHashMap<Uuid, EntryHandle>();

    private final OutriggerServerImpl space;

    EntryHolderSet(OutriggerServerImpl space) {
	this.space = space;
    }

    /**
     * Return the <code>EntryHolder</code> object for the exact class
     * of the <code>Entry</code> object held in <code>bits</code>.  If
     * one doesn't yet exist, it will be created.
     *
     * @see #holderFor(java.lang.String)
     */
    EntryHolder holderFor(EntryRep rep) {
	return holderFor(rep.classFor());
    }

    /**
     * Return the <code>EntryHolder</code> object for the exact class
     * with the given ID. If one doesn't yet exist, it will be created.
     *
     * @see #holderFor(EntryRep)
     */
    EntryHolder holderFor(String className) {
        EntryHolder holder = holders.get(className);
        if (holder == null) {
            holder = new EntryHolder(space, idMap);
            EntryHolder exists = holders.putIfAbsent(className, holder);
            if (exists != null) holder = exists;
        }
        return holder;
    }

    LeasedResource getLeasedResource(Uuid cookie) {
	final EntryHandle handle = handleFor(cookie);
	if (handle == null)
	    return null;
	return handle.getLeasedResource();
    }

    /**
     * Given an entry ID, return the handle associated
     * with it.
     */
    EntryHandle handleFor(Object cookie) {
	return idMap.get(cookie);
    }

    /**
     * Remove the passed handle and associated entry.
     * Assumes the caller holds the lock on handle
     */
    void remove(EntryHandle handle) {
	final EntryHolder holder = holders.get(handle.rep().classFor());
	if (holder == null) return;
	holder.remove(handle, false);
    }

    /**
     * Force all of the holders to reap their <code>FastList</code>s
     */
    void reap() {
 	/* This could take a while, instead of blocking all other
	 * access to holders, clone the contents of holders and
	 * iterate down the clone (we don't do this too often and
	 * holders should never be that big so a shallow copy should
	 * not be that bad, if it did get to be bad caching the
	 * clone would probably work well since we don't add (and
	 * never remove) elements that often.)
	 */
//	final EntryHolder content[];
//	synchronized (holders) {
//	    final Collection values = holders.values();
//	    content = new EntryHolder[values.size()];
//	    values.toArray(content);
//	}
//
//	for (int i=0; i<content.length; i++) {
//            content[i].reap();
//	}
        Iterator<EntryHolder> it = holders.values().iterator();
        while (it.hasNext()){
            it.next().reap();
        }
    }
}
