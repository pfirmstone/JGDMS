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

package com.sun.jini.collection;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * A table that maps a key containing a weak reference to one or more values
 * containing soft references.  The weakly-referenced object in a key are
 * compared for identity using ==.  Entries are removed when either weak
 * references in keys or soft references in values are cleared.  Callers must
 * insure that instances of this class are not accessed concurrently while they
 * are being modified.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public final class WeakSoftTable {

    /* -- Fields -- */

    /** Hash table mapping WeakKeys to Lists of SoftValues. */
    private final Map hash = new HashMap();

    /** Reference queue for cleared keys and values. */
    private final ReferenceQueue queue = new ReferenceQueue();

    /* -- Inner classes -- */

    /**
     * Implemented by classes to permit copying instances into reference queues
     * when they are added to the table and to remove them from the table when
     * the references are cleared.
     */
    public interface RemovableReference {

	/**
	 * Returns a copy of this instance registered with the specified queue.
	 *
	 * @param queue the queue with which this instance should be registered
	 * @return the copy
	 */
	RemovableReference copy(ReferenceQueue queue);

	/**
	 * Called with the containing map when this instance's reference is
	 * cleared, to remove the associated entry from the map.
	 *
	 * @param map the map from which this newly cleared instance should be
	 *	  removed
	 */
	void cleared(Map map);
    }

    /**
     * A key that maintains a weak reference to an object which should be
     * compared by object identity.
     */
    public static class WeakKey extends WeakReference
	implements RemovableReference
    {
	/** Whether the key was null, as opposed to being cleared. */
	private final boolean nullKey;

	/**
	 * The hash code of the key.  Store it so that we can still use it when
	 * comparing hash buckets in the hash table after the key has been
	 * cleared.
	 */
	private final int hashCode;

	/**
	 * Creates a key that holds a weak reference to the argument and
	 * compares it using ==.
	 */
	public WeakKey(Object key) {
	    super(key);
	    nullKey = key == null;
	    hashCode = getClass().hashCode() ^ System.identityHashCode(key);
	}

	/** Creates a copy of the key registered with the queue. */
	protected WeakKey(WeakKey weakKey, ReferenceQueue queue) {
	    super(weakKey.get(), queue);
	    nullKey = weakKey.nullKey;
	    hashCode = weakKey.hashCode;
	}

	public RemovableReference copy(ReferenceQueue queue) {
	    return new WeakKey(this, queue);
	}

	public void cleared(Map map) {
	    map.remove(this);
	}

	public int hashCode() {
	    return hashCode;
	}

	/**
	 * Returns true if the argument is an instance of the same concrete
	 * class, and if both objects had null keys, or if neither object has
	 * had its weak key cleared and their values are ==.
	 */
	public boolean equals(Object o) {
	    if (this == o) {
		return true;
	    } else if (o == null || o.getClass() != getClass()) {
		return false;
	    }
	    WeakKey weakKey = (WeakKey) o;
	    if (weakKey.nullKey != nullKey) {
		return false;
	    } else if (nullKey) {
		return true;
	    }
	    Object key = weakKey.get();
	    return key != null && key == get();
	}
    }

    /** A value that maintains a soft reference to an object. */
    public static class SoftValue extends SoftReference
	implements RemovableReference
    {
	/**
	 * The associated key.  Used to remove the hash table entry when the
	 * value is cleared.
	 */
	protected final WeakKey key;

	/**
	 * Creates a value for the associated key that retains a soft reference
	 * to value.
	 */
	public SoftValue(WeakKey key, Object value) {
	    super(value);
	    this.key = key;
	}

	/** Creates a copy of the value registered with the queue. */
	protected SoftValue(SoftValue softValue, ReferenceQueue queue) {
	    super(softValue.get(), queue);
	    key = softValue.key;
	}

	public RemovableReference copy(ReferenceQueue queue) {
	    return new SoftValue(this, queue);
	}

	public void cleared(Map map) {
	    List list = (List) map.get(key);
	    if (list != null && list.remove(this) && list.isEmpty()) {
		map.remove(key);
	    }
	}
    }

    /* -- Constructors -- */

    /** Creates an instance of this class. */
    public WeakSoftTable() { }

    /* -- Methods -- */

    /**
     * Removes all invalidated entries from the map, that is, removes all
     * entries whose keys or values have been discarded.  This method should be
     * invoked once by each public mutator in this class.
     */
    private void processQueue() {
	Reference ref;
	while ((ref = queue.poll()) != null) {
	    if (ref instanceof RemovableReference) {
		((RemovableReference) ref).cleared(hash);
	    }
	}
    }

    /**
     * Returns the value associated with the specified key and index, or null
     * if not found.  Values are stored in order, so if a given index is null,
     * then values for higher index values will also be null.
     */
    public SoftValue get(WeakKey key, int index) {
	List list = (List) hash.get(key);
	if (list != null && index < list.size()) {
	    return (SoftValue) list.get(index);
	} else {
	    return null;
	}
    }

    /**
     * Associates an additional value with the specified key.  The value is
     * added at the next open index.
     */
    public void add(WeakKey key, SoftValue value) {
	processQueue();
	List list = (List) hash.get(key);
	if (list == null) {
	    list = new LinkedList();
	    hash.put(key.copy(queue), list);
	}
	list.add(value.copy(queue));
    }

    /**
     * Removes and returns the index'th value associated with the specified
     * key.  Returns null if the item is not found.
     */
    public SoftValue remove(WeakKey key, int index) {
	processQueue();
	List list = (List) hash.get(key);
	if (list != null && index < list.size()) {
	    return (SoftValue) list.remove(index);
	} else {
	    return null;
	}
    }

    /** Returns a string representation of this object. */
    public String toString() {
	return hash.toString();
    }
}
