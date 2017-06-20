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
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Map of keys to softly-referenced values which automatically removes mappings
 * for garbage-collected values.  This is a simplified version of
 * sun.misc.SoftCache.
 *
 * @author Sun Microsystems, Inc.
 * @param <K>
 * @param <V>
 *
 * @since 2.0
 */
public class SoftCache<K,V> {

    private final Map<K,Value<V>> map = new HashMap<K,Value<V>>();
    private final ReferenceQueue<V> queue = new ReferenceQueue<V>();

    /**
     * Associates value with given key, returning value previously associated
     * with key, or null if none.
     * @param key
     * @param value
     * @return 
     */
    public V put(K key, V value) {
	processQueue();
	Value<V> v = Value.create(key, value, queue);
	return Value.strip(map.put(key, v), true);
    }

    /**
     * Returns value associated with given key, or null if none.
     * @param key
     * @return 
     */
    public V get(Object key) {
	processQueue();
	return Value.strip(map.get(key), false);
    }

    /**
     * Removes association for given key, returning value previously associated
     * with key, or null if none.
     * @param key
     * @return 
     */
    public V remove(Object key) {
	processQueue();
	return Value.strip(map.remove(key), true);
    }

    /**
     * Removes all associations from this map.
     */
    public void clear() {
	processQueue();
	for (Iterator<Value<V>> i = map.values().iterator(); i.hasNext(); ) {
	    Value<V> v = i.next();
	    if (v != null) {
		v.drop();
	    }
	}
	map.clear();
    }

    private void processQueue() {
	Reference<? extends V> v;
	while ((v = queue.poll()) != null) {
	    if (((Value)v).key != Value.DROPPED) {
		map.remove(((Value)v).key);
	    }
	}
    }

    private static class Value<V> extends SoftReference<V> {

	static final Object DROPPED = new Object();
	Object key;

	static <V> Value<V> create(Object k, V v, ReferenceQueue<V> q) {
	    return (v != null) ? new Value<V>(k, v, q) : null;
	}

	static <V> V strip(Value<V> o, boolean drop) {
	    V v = null;
	    if (o != null) {
		v = o.get();
		if (drop) {
		    o.drop();
		}
	    }
	    return v;
	}

	void drop() {
	    clear();
	    key = DROPPED;
	}

	private Value(Object k, V v, ReferenceQueue<V> q) {
	    super(v, q);
	    key = k;
	}
    }
}
