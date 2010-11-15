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
package com.sun.jini.lease;

import java.util.Map;
import java.util.Set;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseMap;

/**
 * A base class for implementing LeaseMaps.  This class implements all
 * of the Map methods, and ensures keys and values are of the correct type.
 * The subclass is responsible for implementing the actual LeaseMap methods:
 * canContainKey, renewAll, and cancelAll, and serialization of any subclass
 * state.
 *
 * @author Sun Microsystems, Inc.
 *
 */
public abstract class AbstractLeaseMap implements LeaseMap {
    /**
     * Map from Lease to Long(duration), where all leases have the same
     * destination.
     *
     * @serial
     */
    protected final Map map;

    /**
     * Default to using a small HashMap.  It is assumed that
     * canContainKey(lease) is true.
     */
    protected AbstractLeaseMap(Lease lease, long duration) {
	this(new java.util.HashMap(13), lease, duration);
    }

    /**
     * Provide a map of your choice.  It is assumed that
     * canContainKey(lease) is true.
     */
    protected AbstractLeaseMap(Map map, Lease lease, long duration) {
	this.map = map;
	map.put(lease, new Long(duration));
    }

    /** Check that the key is valid for this map */
    protected void checkKey(Object key) {
	if (!canContainKey(key))
	    throw new IllegalArgumentException(
				       "key is not valid for this LeaseMap");
    }

    /** Check that the value is a Long */
    protected static void checkValue(Object value) {
	if (!(value instanceof Long))
	    throw new IllegalArgumentException("value is not a Long");
    }

    // inherit javadoc
    public int size() {
	return map.size();
    }

    // inherit javadoc
    public boolean isEmpty() {
	return map.isEmpty();
    }

    // inherit javadoc
    public boolean containsKey(Object key) {
	checkKey(key);
	return map.containsKey(key);
    }

    // inherit javadoc
    public boolean containsValue(Object value) {
	checkValue(value);
	return map.containsValue(value);
    }

    // inherit javadoc
    public Object get(Object key) {
	checkKey(key);
	return map.get(key);
    }

    // inherit javadoc
    public Object put(Object key, Object value) {
	checkKey(key);
	checkValue(value);
	return map.put(key, value);
    }

    // inherit javadoc
    public Object remove(Object key) {
	checkKey(key);
	return map.remove(key);
    }

    /* Can't use map.putAll here, because we need to ensure checking
     * of the keys and values.
     */
    // inherit javadoc
    public void putAll(Map m) {
	Iterator iter = m.entrySet().iterator();
	while (iter.hasNext()) {
	    Map.Entry e = (Map.Entry)iter.next();
	    put(e.getKey(), e.getValue());
	}
    }

    // inherit javadoc
    public void clear() {
	map.clear();
    }

    // inherit javadoc
    public Set keySet() {
	return map.keySet();
    }

    // inherit javadoc
    public Collection values() {
	return map.values();
    }

    /* We have to wrap the set so that we can do type checking on
     * Map.Entry.setValue.
     */
    // inherit javadoc
    public Set entrySet() {
	return new EntrySet(map.entrySet());
    }

    // inherit javadoc
    public boolean equals(Object o) {
	return map.equals(o); // XXX should sameDestination matter?
    }

    // inherit javadoc
    public int hashCode() {
	return map.hashCode();
    }

    /**
     * We use an AbstractSet to minimize the number of places where
     * we have to wrap objects inside new classes.  This could be
     * expensive, but the standard underlying maps (HashMap and TreeMap)
     * also use an AbstractSet for this set, so we're really not
     * making things that much worse.
     */
    private static final class EntrySet extends AbstractSet {
	private final Set set;

	public EntrySet(Set set) {
	    this.set = set;
	}

	/** Wrap so we can do type checking on Map.Entry.setValue. */
	public Iterator iterator() {
	    return new EntryIterator(set.iterator());
	}

	public boolean contains(Object o) {
	    return set.contains(o);
	}

	public boolean remove(Object o) {
	    return set.remove(o);
	}

	public int size() {
	    return set.size();
	}

	public void clear() {
	    set.clear();
	}
    }

    /** A wrapper so that we can wrap each Entry returned. */
    private static final class EntryIterator implements Iterator {
	private final Iterator iter;

	public EntryIterator(Iterator iter) {
	    this.iter = iter;
	}

	public boolean hasNext() {
	    return iter.hasNext();
	}

	public Object next() {
	    return new Entry((Map.Entry)iter.next());
	}

	public void remove() {
	    iter.remove();
	}
    }

    /** Pass through, except for type checking on setValue */
    private static final class Entry implements Map.Entry {
	private final Map.Entry e;

	public Entry(Map.Entry e) {
	    this.e = e;
	}

	public Object getKey() {
	    return e.getKey();
	}

	public Object getValue() {
	    return e.getValue();
	}

	public Object setValue(Object value) {
	    checkValue(value);
	    return e.setValue(value);
	}

	public boolean equals(Object o) {
	    return e.equals(o);
	}

	public int hashCode() {
	    return e.hashCode();
	}
    }
}
