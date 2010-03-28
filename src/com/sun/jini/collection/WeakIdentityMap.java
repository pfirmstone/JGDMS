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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Identity-based weak hash map.  Updated to support Generics and Map<K,V> on
 * 22nd March 2010
 *
 * @param K Object Key used for identity
 * @param V Object Value
 * @author Sun Microsystems, Inc.
 * @author Peter Firmstone 
 * @version 2.0 - Generic Support and Map<K,V> added.
 * @since 2.0
 */
public class WeakIdentityMap<K,V> implements Map<K,V>{

    // REMIND: optimize implementation (clone new java.util.WeakHashMap?)

    private final Map<Key, V> map = new HashMap<Key, V>();
    private final ReferenceQueue queue = new ReferenceQueue();

    /**
     * Associates value with given key, returning value previously associated
     * with key, or null if none.
     */
    public V put(K key, V value) {
	processQueue();
	return map.put(Key.create(key, queue), value);
    }

    /**
     * Returns value associated with given key, or null if none.
     */
    public V get(Object key) {
	processQueue();
	return map.get(Key.create(key, null));
    }

    /**
     * Removes association for given key, returning value previously associated
     * with key, or null if none.
     */
    public V remove(Object key) {
	processQueue();
	return map.remove(Key.create(key, null));
    }

    /**
     * Returns collection containing all values currently held in this map.
     */
    public Collection<V> values() {
	processQueue();
	return map.values();
    }

    /**
     * Removes all associations from this map.
     */
    public void clear() {
	processQueue();
	map.clear();
    }

    private void processQueue() {
	Key k;
	while ((k = (Key) queue.poll()) != null) {
	    map.remove(k);
	}
    }

    private static class Key<T> extends WeakReference<T> {

	private final int hash;

        @SuppressWarnings("unchecked")
	static Key create(Object k, ReferenceQueue q) {
	    //if (k == null) {return null;} Not so sure we should return null
            if (q == null) {return new Key(k);} 
            return new Key(k, q);
	}

	private Key(T k) {
	    super(k);
	    hash = System.identityHashCode(k);
	}

	private Key(T k, ReferenceQueue<? super T> q) {
	    super(k, q);
	    hash = System.identityHashCode(k);
	}

        @Override
	public boolean equals(Object o) {
	    if (this == o) {
		return true;
	    } else if (!(o instanceof Key)) {
		return false;
	    }
	    Object k1 = get(), k2 = ((Key) o).get();
	    return (k1 != null && k2 != null && k1 == k2);
	}

        @Override
	public int hashCode() {
	    return hash;
	}
    }

    public int size() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public boolean isEmpty() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public boolean containsKey(Object key) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public boolean containsValue(Object value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void putAll(Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Set<K> keySet() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Set<Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
