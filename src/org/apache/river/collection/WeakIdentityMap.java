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

package org.apache.river.collection;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Identity-based weak hash map.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 2.0
 */
public class WeakIdentityMap<K,V> {

    // REMIND: optimize implementation (clone new java.util.WeakHashMap?)

    private final Map<Key<K>,V> map = new HashMap<Key<K>,V>();
    private final ReferenceQueue<K> queue = new ReferenceQueue<K>();

    /**
     * Associates value with given key, returning value previously associated
     * with key, or null if none.
     * @param key
     * @param value
     * @return 
     */
    public V put(K key, V value) {
	processQueue();
	return map.put(Key.create(key, queue), value);
    }

    /**
     * Returns value associated with given key, or null if none.
     * @param key
     * @return 
     */
    public V get(Object key) {
	processQueue();
	return map.get(Key.create(key, null));
    }

    /**
     * Removes association for given key, returning value previously associated
     * with key, or null if none.
     * @param key
     * @return 
     */
    public V remove(Object key) {
	processQueue();
	return map.remove(Key.create(key, null));
    }

    /**
     * Returns collection containing all values currently held in this map.
     * @return 
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
	Reference<? extends K> k;
	    while ((k = queue.poll()) != null) {
	    map.remove(k);
	}
    }

    private static class Key<T> extends WeakReference<T> {

	private final int hash;

	static <T> Key<T> create(T k, ReferenceQueue<T> q) {
	    if (k == null) {
		return null;
	    } else if (q == null) {
		return new Key<T>(k);
	    } else {
		return new Key<T>(k, q);
	    }
	}

	private Key(T k) {
	    super(k);
	    hash = System.identityHashCode(k);
	}

	private Key(T k, ReferenceQueue<T> q) {
	    super(k, q);
	    hash = System.identityHashCode(k);
	}

	public boolean equals(Object o) {
	    if (this == o) {
		return true;
	    } else if (!(o instanceof Key)) {
		return false;
	    }
	    Object k1 = get(), k2 = ((Key) o).get();
	    return (k1 != null && k2 != null && k1 == k2);
	}

	public int hashCode() {
	    return hash;
	}
    }
}
