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

package org.apache.river.imp.util;


import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A Sofly referenced hash map, safe for concurrent threads.
 * 
 * Based on an underlying ConcurrentHashMap, it doesn't accept null keys.
 *
 * Key's must not be mutated, soing so will cause strange results.
 *
 * @param K - key
 * @param V - value
 * @author Peter Firmstone.
 *
 * @since 2.3
 */
public class ConcurrentSoftMap<K, V> implements ConcurrentMap<K, V> {
    // ConcurrentHashMap must be protected from null values;
    private final ConcurrentHashMap<Key, V> map;
    private final ReferenceQueue queue;
    
    public ConcurrentSoftMap(int initialCapacity, float loadFactor, int concurrencyLevel ){
	map = new ConcurrentHashMap<Key, V>(initialCapacity, loadFactor, concurrencyLevel);
	queue = new ReferenceQueue();
    }
    
    public ConcurrentSoftMap(int initialCapacity, float loadFactor){
	this(initialCapacity, loadFactor, 16);
    }
    
    public ConcurrentSoftMap(int initialCapacity ){
	this(initialCapacity, 0.75F, 16);
    }
    
    public ConcurrentSoftMap(){
	this(16, 0.75F, 16);
    }
    
    /**
     * Associates value with given key, returning value previously associated
     * with key, or null if none.
     * @param key - Key
     * @param value - Value
     * @return previous value or null
     */
    public V put(K key, V value) {
	processQueue();
        if (key == null){return null;}
	return map.put(Key.create(key, queue), value);
    }

    /**
     * Returns value associated with given key, or null if none.
     */
    public V get(Object key) {
	processQueue();
        if (key == null) { return null;}
	return map.get(Key.create(key, null));
    }

    /**
     * Removes association for given key, returning value previously associated
     * with key, or null if none.
     */
    public V remove(Object key) {
	processQueue();
        if (key == null) {return null;}
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

    private static class Key<T> extends SoftReference<T> {
	private final int hash;

        @SuppressWarnings("unchecked")
	static Key create(Object k, ReferenceQueue q) {
            //if (k == null) {return null;} // Perhaps this is incorrect
	    if (q == null) {return new Key(k);}
	    return new Key(k, q);	  
	}
	
	private Key(T k) {
	    super(k);
	    hash = k.hashCode();
	}

	private Key(T k, ReferenceQueue<? super T> q) {
	    super(k, q);
	    hash = k.hashCode();
	}

        @Override
	public boolean equals(Object o) {
	    if (this == o) return true;
	    // Don't worry about hashcode because they're already equal.
	    if (!(o instanceof Key)) return false;    
	    Object k1 = get(), k2 = ((Key) o).get();
	    return (k1 != null && k1.equals(k2));
	}

        @Override
	public int hashCode() {
	    return hash;
	}
    }

    public int size() {
        processQueue();
        return map.size();
    }

    public boolean isEmpty() {
        processQueue();
        return map.isEmpty();
    }

    @SuppressWarnings("unchecked")
    public boolean containsKey(Object key) {
        processQueue();
        if (key == null) {return false;}
        return map.containsKey(new Key(key));
    }

    public boolean containsValue(Object value) {
        processQueue();
        if (value == null) {return false;}
        return map.containsValue(value);
    }
    
    /**
     * Unsupported method
     * @param m
     */
    public void putAll(Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    @SuppressWarnings("unchecked")
    public Set<K> keySet() {
        processQueue();
        Enumeration<Key> keys = map.keys(); //Defensive copy by ConcurrentHashMap
        Set<K> keySet = new HashSet<K>();
        while (keys.hasMoreElements()){
            keySet.add( (K) keys.nextElement().get());
        }
        return keySet;
    }
    
    /**
     * Unsupported method
     * @return
     */
    public Set<Map.Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException("Not supported yet, ever?");
    }

    @SuppressWarnings("unchecked")
    public V putIfAbsent(K key, V value) {
        processQueue();  //may be a slight delay before atomic putIfAbsent
        return map.putIfAbsent(new Key(key), value);       
    }

    @SuppressWarnings("unchecked")
    public boolean remove(Object key, Object value) {
        return map.remove(new Key(key), value);
    }

    @SuppressWarnings("unchecked")
    public boolean replace(K key, V oldValue, V newValue) {
        processQueue();
        return map.replace(new Key(key), oldValue, newValue);
    }

    @SuppressWarnings("unchecked")
    public V replace(K key, V value) {
        processQueue();
        return map.replace(new Key(key), value);
    }
}
