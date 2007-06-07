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
 *
 * @since 2.0
 */
public class SoftCache {

    private final Map map = new HashMap();
    private final ReferenceQueue queue = new ReferenceQueue();

    /**
     * Associates value with given key, returning value previously associated
     * with key, or null if none.
     */
    public Object put(Object key, Object value) {
	processQueue();
	Value v = Value.create(key, value, queue);
	return Value.strip(map.put(key, v), true);
    }

    /**
     * Returns value associated with given key, or null if none.
     */
    public Object get(Object key) {
	processQueue();
	return Value.strip(map.get(key), false);
    }

    /**
     * Removes association for given key, returning value previously associated
     * with key, or null if none.
     */
    public Object remove(Object key) {
	processQueue();
	return Value.strip(map.remove(key), true);
    }

    /**
     * Removes all associations from this map.
     */
    public void clear() {
	processQueue();
	for (Iterator i = map.values().iterator(); i.hasNext(); ) {
	    Value v = (Value) i.next();
	    if (v != null) {
		v.drop();
	    }
	}
	map.clear();
    }

    private void processQueue() {
	Value v;
	while ((v = (Value) queue.poll()) != null) {
	    if (v.key != Value.DROPPED) {
		map.remove(v.key);
	    }
	}
    }

    private static class Value extends SoftReference {

	static final Object DROPPED = new Object();
	Object key;

	static Value create(Object k, Object v, ReferenceQueue q) {
	    return (v != null) ? new Value(k, v, q) : null;
	}

	static Object strip(Object o, boolean drop) {
	    if (o != null) {
		Value v = (Value) o;
		o = v.get();
		if (drop) {
		    v.drop();
		}
	    }
	    return o;
	}

	void drop() {
	    clear();
	    key = DROPPED;
	}

	private Value(Object k, Object v, ReferenceQueue q) {
	    super(v, q);
	    key = k;
	}
    }
}
